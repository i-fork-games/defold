// Copyright 2020-2025 The Defold Foundation
// Copyright 2014-2020 King
// Copyright 2009-2014 Ragnar Svensson, Christian Murray
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

// This file was generated with the command:
// python scripts/gen_trig_lookup.py --bits=7

#include "trig_lookup.h"

namespace dmTrigLookup
{
    const float _COS_TABLE[] = 
    {
        1.0f,
        0.998795456205f,
        0.995184726672f,
        0.989176509965f,
        0.980785280403f,
        0.970031253195f,
        0.956940335732f,
        0.941544065183f,
        0.923879532511f,
        0.903989293123f,
        0.881921264348f,
        0.85772861f,
        0.831469612303f,
        0.803207531481f,
        0.773010453363f,
        0.740951125355f,
        0.707106781187f,
        0.671558954847f,
        0.634393284164f,
        0.595699304492f,
        0.55557023302f,
        0.514102744193f,
        0.471396736826f,
        0.42755509343f,
        0.382683432365f,
        0.336889853392f,
        0.290284677254f,
        0.242980179903f,
        0.195090322016f,
        0.146730474455f,
        0.0980171403296f,
        0.0490676743274f,
        6.12323399574e-17f,
        -0.0490676743274f,
        -0.0980171403296f,
        -0.146730474455f,
        -0.195090322016f,
        -0.242980179903f,
        -0.290284677254f,
        -0.336889853392f,
        -0.382683432365f,
        -0.42755509343f,
        -0.471396736826f,
        -0.514102744193f,
        -0.55557023302f,
        -0.595699304492f,
        -0.634393284164f,
        -0.671558954847f,
        -0.707106781187f,
        -0.740951125355f,
        -0.773010453363f,
        -0.803207531481f,
        -0.831469612303f,
        -0.85772861f,
        -0.881921264348f,
        -0.903989293123f,
        -0.923879532511f,
        -0.941544065183f,
        -0.956940335732f,
        -0.970031253195f,
        -0.980785280403f,
        -0.989176509965f,
        -0.995184726672f,
        -0.998795456205f,
        -1.0f,
        -0.998795456205f,
        -0.995184726672f,
        -0.989176509965f,
        -0.980785280403f,
        -0.970031253195f,
        -0.956940335732f,
        -0.941544065183f,
        -0.923879532511f,
        -0.903989293123f,
        -0.881921264348f,
        -0.85772861f,
        -0.831469612303f,
        -0.803207531481f,
        -0.773010453363f,
        -0.740951125355f,
        -0.707106781187f,
        -0.671558954847f,
        -0.634393284164f,
        -0.595699304492f,
        -0.55557023302f,
        -0.514102744193f,
        -0.471396736826f,
        -0.42755509343f,
        -0.382683432365f,
        -0.336889853392f,
        -0.290284677254f,
        -0.242980179903f,
        -0.195090322016f,
        -0.146730474455f,
        -0.0980171403296f,
        -0.0490676743274f,
        -1.83697019872e-16f,
        0.0490676743274f,
        0.0980171403296f,
        0.146730474455f,
        0.195090322016f,
        0.242980179903f,
        0.290284677254f,
        0.336889853392f,
        0.382683432365f,
        0.42755509343f,
        0.471396736826f,
        0.514102744193f,
        0.55557023302f,
        0.595699304492f,
        0.634393284164f,
        0.671558954847f,
        0.707106781187f,
        0.740951125355f,
        0.773010453363f,
        0.803207531481f,
        0.831469612303f,
        0.85772861f,
        0.881921264348f,
        0.903989293123f,
        0.923879532511f,
        0.941544065183f,
        0.956940335732f,
        0.970031253195f,
        0.980785280403f,
        0.989176509965f,
        0.995184726672f,
        0.998795456205f,
    };

    const float* COS_TABLE = _COS_TABLE;
}
