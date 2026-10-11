package com.wenxuan106.botpair;

/** Starts Node.js (libnode.so) inside this process. Node can only be started once per process. */
final class NodeRunner {
    private NodeRunner() {}

    /** Runs `node args...` and blocks until Node exits; returns its exit code (or a negative error). */
    static native int start(String[] args, String[] env, String logPath);

    static void loadLibraries() {
        System.loadLibrary("emutls");
        System.loadLibrary("c++_shared");
        System.loadLibrary("node");
        System.loadLibrary("nodeglue");
    }
}
