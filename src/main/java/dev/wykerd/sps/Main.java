package dev.wykerd.sps;

import java.net.URI;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Logger logger = Logger.getLogger(Main.class.getName());
        logger.info("VASTClient test program");

        VASTClient client = new VASTClient(logger, new Point(10, 10), "JavaC");

        client.onPublication("test_channel", message -> {
            logger.info("Received message: " + message);
        });

        client.connect(URI.create("ws://127.0.0.1:20000")).join();

        logger.info("MAIN: Ready to go!");

        client.subscribe(
            new CircularRegion(new Point(15, 15), 10),
            "test_channel",
            false
        );

        Thread.sleep(100);

        client.publish(new CircularRegion(new Point(20, 20), 10), "test_channel", "hello_world");
    }
}