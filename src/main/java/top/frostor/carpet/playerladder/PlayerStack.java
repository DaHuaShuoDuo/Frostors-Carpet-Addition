package top.frostor.carpet.playerladder;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Dependency-free ride-chain policy used by {@link SharedHandler}.
 */
final class PlayerStack {
    private PlayerStack() {
    }

    static <T> T highestOrSelf(
            T vehicle,
            T newPassenger,
            int limit,
            Predicate<T> hasPassenger,
            Function<T, T> firstPassenger
    ) {
        int hops = 0;
        while (hasPassenger.test(vehicle)) {
            vehicle = firstPassenger.apply(vehicle);
            if(vehicle == null || vehicle == newPassenger || hops++ >= limit) return null;
        }
        return vehicle;
    }

    /**
     * Checks the resulting ride chain when the configurable player stack extension is disabled.
     */
    static <T> boolean allowsRideChain(
            boolean rideExtension,
            T vehicle,
            T newPassenger,
            Predicate<T> isPlayer,
            Function<T, T> vehicleOf
    ) {
        if(rideExtension) return true;

        int players = isPlayer.test(newPassenger) ? 1 : 0;
        Set<T> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for(T entity = vehicle; entity != null; entity = vehicleOf.apply(entity)) {
            if(!visited.add(entity)) return false;
            if(isPlayer.test(entity) && ++players >= 3) return false;
        }
        return true;
    }
}
