package com.kv.thrift;

import com.kv.thrift.RaftService;
import com.kv.thrift.RequestVoteRequest;
import com.kv.thrift.RequestVoteResponse;
import org.apache.thrift.protocol.TBinaryProtocol;

import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.layered.TFramedTransport;

public class RaftClient {
    public static void main(String[] args) {
        TTransport transport = null;
        try {
            transport = new TFramedTransport(new TSocket("127.0.0.1", 18101), 64 * 1024 * 1024);
            transport.open();

            TBinaryProtocol protocol = new TBinaryProtocol(transport);
            RaftService.Client client = new RaftService.Client(protocol);

            // 示例：发送 RequestVote 请求
            RequestVoteRequest request = new RequestVoteRequest(
                    1,            // term
                    "candidate1", // candidateId
                    0,            // lastLogIndex
                    0             // lastLogTerm
            );

            RequestVoteResponse response = client.requestVote(request);
            System.out.println("Vote Granted: " + response.voteGranted + ", Term: " + response.term);

            transport.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (transport != null && transport.isOpen()) {
                transport.close();
            }
        }
    }
}
