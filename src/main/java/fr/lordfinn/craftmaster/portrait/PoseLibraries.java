package fr.lordfinn.craftmaster.portrait;

/**
 * The two pose libraries a game can hold: the server's (read from poses.json) and the copy the client received.
 * In single player both live in the same game, hence two separate slots.
 */
public final class PoseLibraries {

    private PoseLibraries() {}

    private static volatile PoseLibrary server = PoseLibrary.withPresets();
    private static volatile PoseLibrary client = PoseLibrary.withPresets();

    public static PoseLibrary get(boolean clientSide) {
        return clientSide ? client : server;
    }

    public static PoseLibrary server() {
        return server;
    }

    public static void setServer(PoseLibrary library) {
        server = library;
    }

    public static PoseLibrary client() {
        return client;
    }

    public static void setClient(PoseLibrary library) {
        client = library;
    }
}
