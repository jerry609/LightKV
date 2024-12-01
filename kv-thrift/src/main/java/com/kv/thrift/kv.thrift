namespace java com.kv.thrift

exception KVServiceException {
    1: string message
    2: i32 errorCode
}

service KVService {
    binary get(1: binary key) throws (1: KVServiceException e)
    void put(1: binary key, 2: binary value) throws (1: KVServiceException e)
    void delete(1: binary key) throws (1: KVServiceException e)
}

