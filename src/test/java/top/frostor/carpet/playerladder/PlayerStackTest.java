package top.frostor.carpet.playerladder;

public final class PlayerStackTest {
    private static int checks;

    private PlayerStackTest() {
    }

    public static void main(String[] args) {
        returnsSelfForAnEmptyChain();
        followsPassengersToTheTop();
        rejectsTheRequestedPassenger();
        enforcesTheTraversalLimit();
        enforcesTheTwoPlayerLimit();
        allowsExtendedPlayerChains();
        countsOnlyPlayers();
        rejectsCyclicChains();

        System.out.println("PlayerStackTest passed " + checks + " checks");
    }

    private static void returnsSelfForAnEmptyChain() {
        Node root = new Node(false);
        check(
                PlayerStack.highestOrSelf(root, new Node(false), 16, Node::isVehicle, Node::getFirstPassenger) == root,
                "an empty chain must return the original vehicle"
        );
    }

    private static void followsPassengersToTheTop() {
        Node root = new Node(false);
        Node middle = new Node(true);
        Node top = new Node(false);
        root.setFirstPassenger(middle);
        middle.setFirstPassenger(top);

        check(
                PlayerStack.highestOrSelf(root, new Node(false), 16, Node::isVehicle, Node::getFirstPassenger) == top,
                "the highest passenger must be returned"
        );
    }

    private static void rejectsTheRequestedPassenger() {
        Node root = new Node(false);
        Node existingPassenger = new Node(true);
        root.setFirstPassenger(existingPassenger);

        check(
                PlayerStack.highestOrSelf(
                        root,
                        existingPassenger,
                        16,
                        Node::isVehicle,
                        Node::getFirstPassenger
                ) == null,
                "an entity already in the chain must not be inserted again"
        );
    }

    private static void enforcesTheTraversalLimit() {
        Node root = new Node(false);
        Node passenger = new Node(true);
        root.setFirstPassenger(passenger);

        check(
                PlayerStack.highestOrSelf(root, new Node(false), 0, Node::isVehicle, Node::getFirstPassenger) == null,
                "a zero traversal limit must reject following a passenger"
        );
    }

    private static void enforcesTheTwoPlayerLimit() {
        Node root = player();
        Node middle = player();
        Node top = player();
        top.setVehicle(middle);
        middle.setVehicle(root);

        check(
                !PlayerStack.allowsRideChain(
                        false,
                        top,
                        player(),
                        Node::isPlayer,
                        Node::getVehicle
                ),
                "three players must be rejected when ride extension is disabled"
        );
    }

    private static void allowsExtendedPlayerChains() {
        Node root = player();
        Node middle = player();
        Node top = player();
        top.setVehicle(middle);
        middle.setVehicle(root);

        check(
                PlayerStack.allowsRideChain(
                        true,
                        top,
                        player(),
                        Node::isPlayer,
                        Node::getVehicle
                ),
                "the ride extension must allow three or more players"
        );
    }

    private static void countsOnlyPlayers() {
        Node root = player();
        Node nonPlayer = new Node(false);
        nonPlayer.setVehicle(root);

        check(
                PlayerStack.allowsRideChain(
                        false,
                        nonPlayer,
                        player(),
                        Node::isPlayer,
                        Node::getVehicle
                ),
                "non-player vehicles must not count toward the player limit"
        );
    }

    private static void rejectsCyclicChains() {
        Node first = player();
        Node second = player();
        first.setVehicle(second);
        second.setVehicle(first);

        check(
                !PlayerStack.allowsRideChain(
                        false,
                        first,
                        new Node(false),
                        Node::isPlayer,
                        Node::getVehicle
                ),
                "cyclic vehicle chains must be rejected"
        );
    }

    private static Node player() {
        return new Node(true);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    private static final class Node {
        private final boolean player;
        private Node vehicle;
        private Node firstPassenger;

        private Node(boolean player) {
            this.player = player;
        }

        private boolean isPlayer() {
            return player;
        }

        private boolean isVehicle() {
            return firstPassenger != null;
        }

        private Node getVehicle() {
            return vehicle;
        }

        private Node getFirstPassenger() {
            return firstPassenger;
        }

        private void setVehicle(Node vehicle) {
            this.vehicle = vehicle;
        }

        private void setFirstPassenger(Node firstPassenger) {
            this.firstPassenger = firstPassenger;
        }
    }
}
