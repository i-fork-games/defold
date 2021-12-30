// Copyright 2021 The Defold Foundation
// Licensed under the Defold License version 1.0 (the "License"); you may not use
// this file except in compliance with the License.
//
// You may obtain a copy of the License, together with FAQs at
// https://www.defold.com/license
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#ifndef DMSDK_FILE_DESCRIPTOR_H
#define DMSDK_FILE_DESCRIPTOR_H

#include <dmsdk/dlib/array.h>
#if defined(_WIN32)
#include <winsock2.h>
#else
#include <poll.h>
#endif

/*# SDK File Descriptor API documentation
 * File Descriptor functions.
 *
 * @document
 * @name FileDescriptor
 * @namespace dmFileDescriptor
 * @path engine/dlib/src/dmsdk/dlib/file_descriptor.h
 */
namespace dmFileDescriptor
{

    /*# file descriptor result
     * File descriptor result
     * @enum
     * @name Result
     * @member RESULT_OK 0
     */
    enum Result
    {
        RESULT_OK             = 0,
        RESULT_TIMEOUT        = 1,
        RESULT_ERROR          = 2
    };

    /*#
     * Poll events
     * @enum
     * @name PollEvent
     * @member EVENT_READ
     * @member EVENT_WRITE
     * @member EVENT_ERROR
     */
    enum PollEvent
    {
        EVENT_READ   = 0,
        EVENT_WRITE  = 1,
        EVENT_ERROR  = 2,
    };

    /*#
     * Poller
     * @struct
     * @name Poller
     */
    struct Poller
    {
        #if defined(_WIN32)
        dmArray<WSAPOLLFD> m_Pollfds;
        #else
        dmArray<pollfd> m_Pollfds;
        #endif
    };


    /*#
     * Set capacity pf poller.
     * @name PollerSetCapacity
     * @param poller [type:Poller*] Poller
     * @param capacity [type:uint32_T] Capacity
     */
    void PollerSetCapacity(Poller* poller, uint32_t capacity);

    /*#
     * Clear event from poller.
     * @name PollerClearEvent
     * @param poller [type:Poller*] Poller
     * @param event [type:PollEvent] Event to clear
     * @param fd [type:int] File descriptor to clear
     * @return [type:void]
     */
    void PollerClearEvent(Poller* poller, PollEvent event, int fd);

    /*#
     * Set file descriptor event to poll for
     * @name PollerSetEvent
     * @param poller [type:Poller*] Poller
     * @param event [type:PollEvent] Event to set
     * @param fd [type:int] File descriptor to clear
     * @return [type:void]
     */
    void PollerSetEvent(Poller* poller, PollEvent event, int fd);

    /*#
     * Check if event exists for file descriptor
     * @name PollerHasEvent
     * @param poller [type:Poller*] Poller
     * @param event [type:PollEvent] Event to check
     * @param fd [type:int] File descriptor to clear
     * @return [type:bool] True if event exists.
     */
    bool PollerHasEvent(Poller* poller, PollEvent event, int fd);

    /*#
     * Reset poller.
     * @name SelectorZero
     * @param spoller [type:Poller*] Poller
     * @return [type:void]
     */
    void PollerReset(Poller* poller);

    /*#
     * Wait for event
     * @name Wait
     * @param poller [type:Poller*] Poller
     * @param timeout [type:int] Timeout. For blocking pass -1. (microseconds)
     * @return [type:Result] Non-negative value on success, 0 on timeout and
     * -1 on error with errno set to indicate the error
     */
    int Wait(Poller* poller, int timeout);
}

#endif // DMSDK_FILE_DESCRIPTOR_H
