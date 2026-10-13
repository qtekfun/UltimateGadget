/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.*

/**
 * How a workout is presented: which metrics lead and whether a map makes sense. A treadmill or an
 * elliptical happens inside a gym, so it never gets a map even if the watch logged a few GPS fixes.
 */
enum class WorkoutProfile {
    /** Distance sports done outside: the route, pace and elevation lead. */
    OUTDOOR,
    /** Gym machines: no route; heart rate, cadence and effort lead. */
    INDOOR,
    /** Pool or open water: laps, SWOLF and stroke rate lead. */
    SWIM,
    /** Golf rounds: the scorecard screen, with heart rate and effort below. */
    GOLF,
    /** Everything else (strength, classes, team sports...): heart rate and effort. */
    GENERIC;

    /** Whether a recorded track is worth showing as a map for this profile. */
    fun showsMap(trackPoints: Int): Boolean = when (this) {
        INDOOR -> false
        else -> trackPoints >= 2
    }

    /** Whether distance is a headline metric (the list and the dashboard show it first). */
    val leadsWithDistance: Boolean get() = this == OUTDOOR || this == SWIM

    companion object {
        private val OUTDOOR_KINDS: Set<ActivityKind> = setOf(
            RUNNING, OUTDOOR_RUNNING, TRAIL_RUN, CROSS_COUNTRY_RUNNING, STREET_RUNNING, ULTRA_RUN,
            TRACK_RUN, WALKING, OUTDOOR_WALKING, RACE_WALKING, HIKING, TRAIL_HIKE, MOUNTAIN_HIKE,
            TREKKING, MOUNTAINEERING, SNOWSHOE, NAVIGATE, ORIENTEERING, GEOCACHING, ADVENTURE_RACE,
            CYCLING, OUTDOOR_CYCLING, ROAD_BIKE, MOUNTAIN_BIKE, GRAVEL_BIKE, E_BIKE, E_MOUNTAIN_BIKE,
            BIKE_COMMUTE, BIKE_TOUR, CYCLO_CROSS, CYCLING_DOWNHILL, BMX, HANDCYCLING,
            PUSH_WALK_SPEED, PUSH_RUN_SPEED, INLINE_SKATING, ROLLER_SKATING, SKATEBOARDING,
            SKIING, CROSS_COUNTRY_SKIING, XC_CLASSIC_SKI, XC_SKATE_SKI, BACKCOUNTRY_SKIING,
            BACKCOUNTRY_SNOWBOARDING, SNOWBOARDING, ROWING, KAYAKING, CANOEING, PADDLING,
            STAND_UP_PADDLEBOARDING, DRAGON_BOAT, TRIATHLON, MULTISPORT,
        )

        private val INDOOR_KINDS: Set<ActivityKind> = setOf(
            TREADMILL, INDOOR_RUNNING, INDOOR_TRACK_RUNNING, VIRTUAL_RUN, INDOOR_WALKING,
            INDOOR_PUSH_WALK_SPEED, INDOOR_PUSH_RUN_SPEED, AIR_WALKER, INDOOR_CYCLING, SPINNING,
            DYNAMIC_CYCLE, HANDCYCLING_INDOOR, ELLIPTICAL_TRAINER, CROSS_TRAINER, ROWING_MACHINE,
            STAIR_STEPPER, STAIR_CLIMBER, STEPPER, STAIRS, FLOOR_CLIMBING, INDOOR_SKIING,
            JUMP_ROPING, FITNESS_EQUIPMENT,
        )

        @JvmStatic
        fun of(kind: ActivityKind): WorkoutProfile = when {
            kind == ActivityKind.GOLF -> GOLF
            ActivityKind.isSwimActivity(kind) -> SWIM
            kind in INDOOR_KINDS -> INDOOR
            kind in OUTDOOR_KINDS -> OUTDOOR
            else -> GENERIC
        }
    }
}
