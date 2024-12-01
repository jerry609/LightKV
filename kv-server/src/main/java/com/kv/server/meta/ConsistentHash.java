package com.kv.server.meta;

import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ConsistentHash<T> {
    private final int numberOfReplicas;
    private final SortedMap<Integer, T> circle = new TreeMap<>();

    public ConsistentHash(int numberOfReplicas, Collection<T> nodes) {
        this.numberOfReplicas = numberOfReplicas;
        for (T node : nodes) {
            add(node);
        }
    }

    public void add(T node) {
        for (int i = 0; i < numberOfReplicas; i++) {
            circle.put(hash(node.toString() + i), node);
        }
    }

    public void remove(T node) {
        for (int i = 0; i < numberOfReplicas; i++) {
            circle.remove(hash(node.toString() + i));
        }
    }

    public T getNode(String key) {
        if (circle.isEmpty()) {
            return null;
        }
        int hash = hash(key);
        if (!circle.containsKey(hash)) {
            SortedMap<Integer, T> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
        }
        return circle.get(hash);
    }

    public List<T> getNodes(String key, int count) {
        if (circle.isEmpty() || count == 0) {
            return Collections.emptyList();
        }

        List<T> nodes = new ArrayList<>(count);
        int hash = hash(key);
        SortedMap<Integer, T> tailMap = circle.tailMap(hash);
        Iterator<Map.Entry<Integer, T>> iterator =
            (!tailMap.isEmpty() ? tailMap : circle).entrySet().iterator();

        while (nodes.size() < count && iterator.hasNext()) {
            nodes.add(iterator.next().getValue());
        }

        return nodes;
    }

    public void updateNodes(Collection<T> nodes) {
        circle.clear();
        for (T node : nodes) {
            add(node);
        }
    }

    private int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes());
            return Math.abs(bytes[3] << 24 |
                          (bytes[2] & 0xFF) << 16 |
                          (bytes[1] & 0xFF) << 8 |
                          (bytes[0] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
