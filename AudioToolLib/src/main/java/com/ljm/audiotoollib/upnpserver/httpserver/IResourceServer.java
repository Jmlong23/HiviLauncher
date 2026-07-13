package com.ljm.audiotoollib.upnpserver.httpserver;

interface IResourceServer {
    void startServer();

    void stopServer();

    // ----------------------------------------------------------------------------
    // Factory
    // ----------------------------------------------------------------------------
    interface IResourceServerFactory {
        int getPort();

        IResourceServer getInstance();

        // ----------------------------------------------------------------------------
        // ---- implement
        // ----------------------------------------------------------------------------
        final class DefaultResourceServerFactoryImpl implements IResourceServerFactory {
            private final int port;

            public DefaultResourceServerFactoryImpl(int port) {
                this.port = port;
            }

            @Override
            public int getPort() {
                return port;
            }

            @Override
            public IResourceServer getInstance() {
                    return UpnpHttpServer.getInstance(port);
            }
        }
    }
}
