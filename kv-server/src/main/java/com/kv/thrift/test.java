package com.kv.thrift;

import java.nio.ByteBuffer;

public class test {
    public static void main(String []args) {
        byte[] byteArray = new byte[100];
        ByteBuffer byteBuffer = ByteBuffer.allocate(100);

        System.out.println("byte[] length: " + byteArray.length);
        System.out.println("ByteBuffer capacity: " + byteBuffer.capacity());

    }
}
