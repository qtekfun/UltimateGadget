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
package nodomain.freeyourgadget.gadgetbridge.service

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice.State

/**
 * One-watch-at-a-time connection policy.
 *
 * - Only the most recently used watch is ever searched for or reconnected automatically.
 * - While a watch is connecting or connected, no other watch is searched for.
 * - When a watch connects, the searches of the others are aborted.
 *
 * Pure functions so the rules can be unit tested without Bluetooth.
 */
object ConnectionPolicy {

    /** A candidate for automatic reconnection. */
    data class Candidate(val address: String, val lastConnectedTs: Long)

    /** A real link exists or is being established: another watch must not compete for the phone. */
    @JvmStatic
    fun isActive(state: State): Boolean = when (state) {
        State.CONNECTING,
        State.CONNECTED,
        State.INITIALIZING,
        State.AUTHENTICATION_REQUIRED,
        State.AUTHENTICATING,
        State.INITIALIZED -> true
        else -> false
    }

    /** The watch is only looking for the phone (reconnect loop, BLE scan or just seen). */
    @JvmStatic
    fun isSearching(state: State): Boolean = when (state) {
        State.WAITING_FOR_RECONNECT,
        State.WAITING_FOR_SCAN,
        State.SCANNED,
        State.CONNECTING -> true
        else -> false
    }

    /**
     * Address of the watch that may be reconnected automatically: the most recently connected one,
     * or the first candidate when none has ever connected. Null when there are no candidates.
     */
    @JvmStatic
    fun pickLastUsed(candidates: List<Candidate>): String? =
        candidates.maxByOrNull { it.lastConnectedTs }?.address

    /**
     * Whether an automatic attempt at [address] is allowed: it must be the last used watch and
     * no other watch may be active.
     */
    @JvmStatic
    fun allowAutomatic(address: String, lastUsed: String?, otherStates: Collection<State>): Boolean =
        (lastUsed == null || lastUsed == address) && otherStates.none { isActive(it) }
}
