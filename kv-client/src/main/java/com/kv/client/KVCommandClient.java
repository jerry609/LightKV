package com.kv.client;

import java.nio.ByteBuffer;
import java.util.Scanner;

public class KVCommandClient implements AutoCloseable {
    private final KVClient client;
    private final Scanner scanner;

    public KVCommandClient(String host, int port) throws Exception {
        this.client = new KVClient(host, port);
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void close() throws Exception {
        scanner.close();
        if (client != null) {
            client.close();
        }
    }

    public void start() {
        System.out.println("KV Store Command Line Client");
        System.out.println("Available commands: get <key>, put <key> <value>, delete <key>, exit");

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");

            if (parts.length == 0 || parts[0].isEmpty()) {
                continue;
            }

            try {
                switch (parts[0].toLowerCase()) {
                    case "exit":
                        return;
                    case "get":
                        if (parts.length != 2) {
                            System.out.println("Usage: get <key>");
                            continue;
                        }
                        handleGet(parts[1]);
                        break;
                    case "put":
                        if (parts.length != 3) {
                            System.out.println("Usage: put <key> <value>");
                            continue;
                        }
                        handlePut(parts[1], parts[2]);
                        break;
                    case "delete":
                        if (parts.length != 2) {
                            System.out.println("Usage: delete <key>");
                            continue;
                        }
                        handleDelete(parts[1]);
                        break;
                    default:
                        System.out.println("Unknown command: " + parts[0]);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void handleGet(String key) throws Exception {
        ByteBuffer value = client.get(key.getBytes());
        if (value == null) {
            System.out.println("Key not found: " + key);
        } else {
            byte[] bytes = new byte[value.remaining()];
            value.get(bytes);
            System.out.println("Value: " + new String(bytes));
        }
    }

    private void handlePut(String key, String value) throws Exception {
        client.put(key, ByteBuffer.wrap(value.getBytes()));
        System.out.println("Successfully put key: " + key);
    }

    private void handleDelete(String key) throws Exception {
        client.delete(key);
        System.out.println("Successfully deleted key: " + key);
    }

    public static void main(String[] args) {
//        if (args.length != 2) {
//            System.out.println("Usage: KVCommandClient <host> <port>");
//            System.exit(1);
//        }
//
//        String host = args[0];
//        int port;
//        try {
//            port = Integer.parseInt(args[1]);
//        } catch (NumberFormatException e) {
//            System.err.println("Error: Port must be a number");
//            System.exit(1);
//            return;
//        }
    String host="127.0.0.1";
    int port=18091;
        try (KVCommandClient client = new KVCommandClient(host, port)) {
            client.start();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}