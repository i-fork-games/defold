(ns internal.transaction
  "Internal functions that implement the transactional behavior."
  (:require [clojure.core.async :as a]
            [clojure.set :as set]
            [dynamo.util :refer :all]
            [internal.graph :as ig]
            [internal.graph.types :as gt]
            [service.log :as log]))


; ---------------------------------------------------------------------------
; Configuration parameters
; ---------------------------------------------------------------------------
(def maximum-retrigger-count 100)
(def maximum-graph-coloring-recursion 1000)

; ---------------------------------------------------------------------------
; Internal state
; ---------------------------------------------------------------------------
(def ^:dynamic *tx-debug* nil)
(def ^:dynamic *scope* nil)

(def ^:private ^java.util.concurrent.atomic.AtomicInteger next-txid (java.util.concurrent.atomic.AtomicInteger. 1))
(defn- new-txid [] (.getAndIncrement next-txid))

(defmacro txerrstr [ctx & rest]
  `(str (:txid ~ctx) ":" (:txpass ~ctx) " " ~@(interpose " " rest)))


; ---------------------------------------------------------------------------
; Building transactions
; ---------------------------------------------------------------------------
(defn new-node
  "*transaction step* - creates a resource in the project. Expects a
  node. May include an `:_id` key containing a tempid if the resource
  will be referenced again in the same transaction. If supplied,
  _input_ and _output_ are sets of input and output labels,
  respectively.  If not supplied as arguments, the `:input` and
  `:output` keys in the node may will be assigned as the resource's
  inputs and outputs."
  [node]
  [{:type :create-node
    :node node}])

(defn become
  [from-node to-node]
  [{:type     :become
    :node-id  (:_id from-node)
    :to-node  to-node}])

(defn delete-node
  [node]
  [{:type    :delete-node
    :node-id (:_id node)}])

(defn update-property
  "*transaction step* - Expects a node, a property label, and a
  function f (with optional args) to be performed on the current value
  of the property. The node may be a uncommitted, in which case it
  will have a tempid."
  [node pr f args]
  [{:type     :update-property
    :node-id  (:_id node)
    :property pr
    :fn       f
    :args     args}])

(defn set-property
  [node pr v]
  (update-property node pr (fn [& _] v) []))

(defn connect
  "*transaction step* - Creates a transaction step connecting a source node and label (`from-resource from-label`) and a target node and label
(`to-resource to-label`). It returns a value suitable for consumption by [[perform]]. Nodes passed to `connect` may have tempids."
  [from-node from-label to-node to-label]
  [{:type         :connect
    :source-id    (:_id from-node)
    :source-label from-label
    :target-id    (:_id to-node)
    :target-label to-label}])

(defn disconnect
  "*transaction step* - The reverse of [[connect]]. Creates a
  transaction step disconnecting a source node and label
  (`from-resource from-label`) from a target node and label
  (`to-resource to-label`). It returns a value suitable for consumption
  by [[perform]]. Nodes passed to `disconnect` may be tempids."
  [from-node from-label to-node to-label]
  [{:type         :disconnect
    :source-id    (:_id from-node)
    :source-label from-label
    :target-id    (:_id to-node)
    :target-label to-label}])

(defn label
  [label]
  [{:type  :label
    :label label}])

(defn- tempid? [x] (neg? x))
(defn has-tempid? [n] (and (:_id n) (tempid? (:_id n))))
(defn resolve-tempid [ctx x] (when x (if (pos? x) x (get (:tempids ctx) x))))

; ---------------------------------------------------------------------------
; Executing transactions
; ---------------------------------------------------------------------------
(defn- pairs [m] (for [[k vs] m v vs] [k v]))

(defn- mark-activated
  [{:keys [graph] :as ctx} node-id input-label]
  (let [dirty-deps (get (gt/output-dependencies (ig/node graph node-id)) input-label)]
    (update-in ctx [:nodes-affected node-id] set/union dirty-deps)))

(defn- activate-all-outputs
  [{:keys [graph] :as ctx} node-id node]
  (let [all-labels  (concat (gt/outputs node) (keys (gt/properties node)))
        ctx (update-in ctx [:nodes-affected node-id] set/union all-labels)
        all-targets (into #{[node-id nil]} (mapcat #(ig/targets graph node-id %) all-labels))]
    (reduce
      (fn [ctx [target-id target-label]]
        (mark-activated ctx target-id target-label))
      ctx
      all-targets)))

(defmulti perform
  "A multimethod used for defining methods that perform the individual
  actions within a transaction. This is for internal use, not intended
  to be extended by applications.

  Perform takes a transaction context (ctx) and a map (m) containing a
  value for keyword `:type`, and other keys and values appropriate to
  the transformation it represents. Callers should regard the map and
  context as opaque.

  Calls to perform are only executed by [[transact]]. The data
  required for `perform` calls are constructed in action functions,
  such as [[connect]] and [[update-property]]."
  (fn [ctx m] (:type m)))

(defmethod perform :create-node
  [{:keys [graph tempids triggers-to-fire nodes-affected world-ref new-event-loops nodes-added] :as ctx}
   {:keys [node]}]
  (let [next-id     (ig/next-node graph)
        full-node   (assoc node :_id next-id :world-ref world-ref) ;; TODO: can we remove :world-ref from nodes?
        graph-after (ig/add-labeled-node graph (gt/inputs node) (gt/outputs node) full-node)
        full-node   (ig/node graph-after next-id)]
    (assoc ctx
      :graph               graph-after
      :nodes-added         (conj nodes-added next-id)
      :tempids             (assoc tempids (:_id node) next-id)
      :new-event-loops     (if (satisfies? gt/MessageTarget full-node) (conj new-event-loops next-id) new-event-loops)
      :triggers-to-fire    (update-in triggers-to-fire [next-id :added] concat [])
      :nodes-affected      (merge-with set/union nodes-affected {next-id (gt/outputs full-node)}))))

(defn- disconnect-inputs
  [ctx target-node target-label]
  (reduce
    (fn [ctx [in-node in-label]]
      (perform ctx {:type         :disconnect
                    :source-id    in-node
                    :source-label in-label
                    :target-id    (:_id target-node)
                    :target-label target-label}))
    ctx
    (ig/sources (:graph ctx) (:_id target-node) target-label)))

(defn- disconnect-outputs
  [ctx source-node source-label]
  (reduce
    (fn [ctx [target-node target-label]]
      (perform ctx {:type         :disconnect
                    :source-id    source-node
                    :source-label source-label
                    :target-id    (:_id target-node)
                    :target-label target-label}))
    ctx
    (ig/targets (:graph ctx) (:_id source-node) source-label)))

(defmethod perform :become
  [{:keys [graph tempids nodes-affected world-ref new-event-loops old-event-loops] :as ctx}
   {:keys [node-id to-node]}]
  (let [old-node         (ig/node graph node-id)
        to-node-id       (:_id to-node)
        new-node         (merge to-node old-node)

        ;; disconnect inputs that no longer exist
        vanished-inputs  (set/difference (gt/inputs old-node) (gt/inputs new-node))
        ctx              (reduce (fn [ctx in]  (disconnect-inputs ctx node-id  in))  ctx vanished-inputs)

        ;; disconnect outputs that no longer exist
        vanished-outputs (set/difference (gt/outputs old-node) (gt/outputs new-node))
        ctx              (reduce (fn [ctx out] (disconnect-outputs ctx node-id out)) ctx vanished-outputs)

        graph            (ig/transform-node graph node-id (constantly new-node))

        start-loop       (and      (gt/message-target? new-node)  (not (gt/message-target? old-node)))
        end-loop         (and (not (gt/message-target? new-node))      (gt/message-target? old-node))]
    (assoc (activate-all-outputs ctx node-id new-node)
      :graph               graph
      :tempids             (if (tempid? to-node-id) (assoc tempids to-node-id node-id) tempids)
      :new-event-loops     (if start-loop (conj new-event-loops node-id)  new-event-loops)
      :old-event-loops     (if end-loop   (conj old-event-loops old-node) old-event-loops))))

(defmethod perform :delete-node
  [{:keys [graph nodes-deleted old-event-loops nodes-added triggers-to-fire] :as ctx} {:keys [node-id]}]
  (when-not (ig/node graph (resolve-tempid ctx node-id))
    (prn :delete-node "Can't locate node for ID " node-id))
  (let [node-id     (resolve-tempid ctx node-id)
        node        (ig/node graph node-id)
        ctx         (activate-all-outputs ctx node-id node)]
    (assoc ctx
      :graph               (ig/remove-node graph node-id)
      :old-event-loops     (if (gt/message-target? node) (conj old-event-loops node) old-event-loops)
      :nodes-deleted       (assoc nodes-deleted node-id node)
      :nodes-added         (disj nodes-added node-id)
      :triggers-to-fire    (update-in triggers-to-fire [node-id :deleted] concat []))))

(defmethod perform :update-property
  [{:keys [graph triggers-to-fire properties-modified] :as ctx} {:keys [node-id property fn args]}]
  (let [node-id   (resolve-tempid ctx node-id)
        old-value (get (ig/node graph node-id) property)
        new-graph (apply ig/transform-node graph node-id update-in [property] fn args)
        new-value (get (ig/node new-graph node-id) property)]
    (if (= old-value new-value)
      ctx
      (-> ctx
         (mark-activated node-id property)
         (assoc
           :graph               new-graph
           :triggers-to-fire    (update-in triggers-to-fire [node-id :property-touched] concat [property])
           :properties-modified (update-in properties-modified [node-id] conj property))))))

(defmethod perform :connect
  [{:keys [graph triggers-to-fire] :as ctx} {:keys [source-id source-label target-id target-label]}]
  (let [source-id (resolve-tempid ctx source-id)
        target-id (resolve-tempid ctx target-id)]
    (-> ctx
      (mark-activated target-id target-label)
      (assoc
        :graph            (ig/connect graph source-id source-label target-id target-label)
        :triggers-to-fire (update-in triggers-to-fire [target-id :input-connections] concat [target-label])))))

(defmethod perform :disconnect
  [{:keys [graph triggers-to-fire] :as ctx} {:keys [source-id source-label target-id target-label]}]
  (let [source-id (resolve-tempid ctx source-id)
        target-id (resolve-tempid ctx target-id)]
    (-> ctx
      (mark-activated target-id target-label)
      (assoc
        :graph            (ig/disconnect graph source-id source-label target-id target-label)
        :triggers-to-fire (update-in triggers-to-fire [target-id :input-connections] concat [target-label])))))

(defmethod perform :label
  [ctx {:keys [label]}]
  (assoc ctx :label label))

(defn- apply-tx
  [ctx actions]
  (reduce
   (fn [ctx action]
     (cond
       (sequential? action) (apply-tx ctx action)
       :else                (-> ctx
                                (perform action)
                                (update-in [:completed] conj action))))
   ctx
   actions))

(defn- last-seen-node
  [{:keys [graph nodes-deleted]} node-id]
  (or
    (ig/node graph node-id)
    (get nodes-deleted node-id)))

(defn- trigger-activations
  [{:keys [triggers-to-fire] :as ctx}]
  (for [[node-id m] triggers-to-fire
        [kind args] m
        :let [node (last-seen-node ctx node-id)]
        :when node
        [l tr] (-> node gt/node-type gt/triggers (get kind) seq)]
    (if (empty? args)
      [tr node l kind]
      [tr node l kind (set args)])))

(defn- invoke-trigger
  [csub [tr & args]]
  (apply tr csub (:graph csub) args))

(defn- debug-invoke-trigger
  [csub [tr & args :as trvec]]
  (println (txerrstr csub "invoking" tr "on" (:_id (first args)) "with" (rest args)))
  (println (txerrstr csub "nodes triggered" (:nodes-affected csub)))
  (println (txerrstr csub (select-keys csub [:nodes-added :nodes-deleted :outputs-modified :properties-modified])))
  (invoke-trigger csub trvec))

(def ^:private fire-trigger (if *tx-debug* debug-invoke-trigger invoke-trigger))

(defn- process-triggers
  [ctx]
  (when *tx-debug*
    (println (txerrstr ctx "triggers to fire: " (:triggers-to-fire ctx))))
  (update-in ctx [:pending]
             into (remove empty?
                          (mapv
                           #(fire-trigger ctx %)
                           (trigger-activations ctx)))))

(defn- mark-outputs-modified
  [{:keys [graph nodes-affected] :as ctx}]
  (update-in ctx [:outputs-modified]
             #(set/union % (pairs nodes-affected))))

(defn- one-transaction-pass
  [ctx actions]
  (-> (update-in ctx [:txpass] inc)
    (assoc :triggers-to-fire {})
    (assoc :nodes-affected {})
    (apply-tx actions)
    mark-outputs-modified
    process-triggers
    (dissoc :triggers-to-fire)
    (dissoc :nodes-affected)))

(defn- exhaust-actions-and-triggers
  ([ctx]
   (exhaust-actions-and-triggers ctx maximum-retrigger-count))
  ([{[current-action & pending-actions] :pending :as ctx} retrigger-count]
   (assert (< 0 retrigger-count) (txerrstr ctx "Maximum number of trigger executions reached; probable infinite recursion."))
   (if (empty? current-action)
     ctx
     (recur
      (one-transaction-pass (assoc ctx :pending pending-actions) current-action)
      (dec retrigger-count)))))

(def tx-report-keys [:tx-id :new-event-loops :tempids :nodes-added :nodes-deleted :outputs-modified :properties-modified :label])

(defn- finalize-update
  "Makes the transacted graph the new value of the world-state graph."
  [{:keys [graph tx-id] :as ctx}]
  (let [empty-tx? (empty? (:completed ctx))
        graph     (assoc graph :tx-id tx-id)]
    (assoc (select-keys ctx tx-report-keys)
           :graph  graph
           :status (if empty-tx? :empty :ok))))

(defn new-transaction-context
  [world-ref actions]
  (let [current-world @world-ref]
    {:world-ref           world-ref
     :world               current-world
     :graph               current-world
     :tempids             {}
     :new-event-loops     #{}
     :old-event-loops     #{}
     :nodes-added         #{}
     :nodes-modified      #{}
     :nodes-deleted       {}
     :pending             [actions]
     :completed           []
     :tx-id               (new-txid)
     :txpass              0}))

(defn- trace-dependencies
  [{:keys [graph outputs-modified] :as ctx}]
  (update-in ctx [:outputs-modified]
             #(ig/trace-dependencies graph %)))

(defn transact*
  [world-ref ctx]
  (-> ctx
      exhaust-actions-and-triggers
      trace-dependencies
      finalize-update))
