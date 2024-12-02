namespace java com.kv.thrift

struct LogEntry {
  1: i64 index,
  2: i64 term,
  3: binary command,
}

struct RequestVoteRequest {
  1: i64 term,
  2: string candidateId,
  3: i64 lastLogIndex,
  4: i64 lastLogTerm,
}

struct RequestVoteResponse {
  1: i64 term,
  2: bool voteGranted,
}

struct AppendEntriesRequest {
  1: i64 term,
  2: string leaderId,
  3: i64 prevLogIndex,
  4: i64 prevLogTerm,
  5: list<LogEntry> entries,
  6: i64 leaderCommit,
}

struct AppendEntriesResponse {
  1: i64 term,
  2: bool success,
}

service RaftService {
  RequestVoteResponse requestVote(1: RequestVoteRequest request),
  AppendEntriesResponse appendEntries(1: AppendEntriesRequest request),
}
