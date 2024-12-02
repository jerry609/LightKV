/**
 * Autogenerated by Thrift Compiler (0.15.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.kv.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.15.0)", date = "2024-12-01")
public class RequestVoteResponse implements org.apache.thrift.TBase<RequestVoteResponse, RequestVoteResponse._Fields>, java.io.Serializable, Cloneable, Comparable<RequestVoteResponse> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("RequestVoteResponse");

    private static final org.apache.thrift.protocol.TField TERM_FIELD_DESC = new org.apache.thrift.protocol.TField("term", org.apache.thrift.protocol.TType.I64, (short)1);
    private static final org.apache.thrift.protocol.TField VOTE_GRANTED_FIELD_DESC = new org.apache.thrift.protocol.TField("voteGranted", org.apache.thrift.protocol.TType.BOOL, (short)2);

    private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new RequestVoteResponseStandardSchemeFactory();
    private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new RequestVoteResponseTupleSchemeFactory();

    public long term; // required
    public boolean voteGranted; // required

    /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
        TERM((short)1, "term"),
        VOTE_GRANTED((short)2, "voteGranted");

        private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

        static {
            for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
                byName.put(field.getFieldName(), field);
            }
        }

        /**
         * Find the _Fields constant that matches fieldId, or null if its not found.
         */
        @org.apache.thrift.annotation.Nullable
        public static _Fields findByThriftId(int fieldId) {
            switch(fieldId) {
                case 1: // TERM
                    return TERM;
                case 2: // VOTE_GRANTED
                    return VOTE_GRANTED;
                default:
                    return null;
            }
        }

        /**
         * Find the _Fields constant that matches fieldId, throwing an exception
         * if it is not found.
         */
        public static _Fields findByThriftIdOrThrow(int fieldId) {
            _Fields fields = findByThriftId(fieldId);
            if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
            return fields;
        }

        /**
         * Find the _Fields constant that matches name, or null if its not found.
         */
        @org.apache.thrift.annotation.Nullable
        public static _Fields findByName(java.lang.String name) {
            return byName.get(name);
        }

        private final short _thriftId;
        private final java.lang.String _fieldName;

        _Fields(short thriftId, java.lang.String fieldName) {
            _thriftId = thriftId;
            _fieldName = fieldName;
        }

        public short getThriftFieldId() {
            return _thriftId;
        }

        public java.lang.String getFieldName() {
            return _fieldName;
        }
    }

    // isset id assignments
    private static final int __TERM_ISSET_ID = 0;
    private static final int __VOTEGRANTED_ISSET_ID = 1;
    private byte __isset_bitfield = 0;
    public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
    static {
        java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
        tmpMap.put(_Fields.TERM, new org.apache.thrift.meta_data.FieldMetaData("term", org.apache.thrift.TFieldRequirementType.DEFAULT,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
        tmpMap.put(_Fields.VOTE_GRANTED, new org.apache.thrift.meta_data.FieldMetaData("voteGranted", org.apache.thrift.TFieldRequirementType.DEFAULT,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
        metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
        org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(RequestVoteResponse.class, metaDataMap);
    }

    public RequestVoteResponse() {
    }

    public RequestVoteResponse(
            long term,
            boolean voteGranted)
    {
        this();
        this.term = term;
        setTermIsSet(true);
        this.voteGranted = voteGranted;
        setVoteGrantedIsSet(true);
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public RequestVoteResponse(RequestVoteResponse other) {
        __isset_bitfield = other.__isset_bitfield;
        this.term = other.term;
        this.voteGranted = other.voteGranted;
    }

    public RequestVoteResponse deepCopy() {
        return new RequestVoteResponse(this);
    }

    @Override
    public void clear() {
        setTermIsSet(false);
        this.term = 0;
        setVoteGrantedIsSet(false);
        this.voteGranted = false;
    }

    public long getTerm() {
        return this.term;
    }

    public RequestVoteResponse setTerm(long term) {
        this.term = term;
        setTermIsSet(true);
        return this;
    }

    public void unsetTerm() {
        __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __TERM_ISSET_ID);
    }

    /** Returns true if field term is set (has been assigned a value) and false otherwise */
    public boolean isSetTerm() {
        return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __TERM_ISSET_ID);
    }

    public void setTermIsSet(boolean value) {
        __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __TERM_ISSET_ID, value);
    }

    public boolean isVoteGranted() {
        return this.voteGranted;
    }

    public RequestVoteResponse setVoteGranted(boolean voteGranted) {
        this.voteGranted = voteGranted;
        setVoteGrantedIsSet(true);
        return this;
    }

    public void unsetVoteGranted() {
        __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __VOTEGRANTED_ISSET_ID);
    }

    /** Returns true if field voteGranted is set (has been assigned a value) and false otherwise */
    public boolean isSetVoteGranted() {
        return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __VOTEGRANTED_ISSET_ID);
    }

    public void setVoteGrantedIsSet(boolean value) {
        __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __VOTEGRANTED_ISSET_ID, value);
    }

    public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
        switch (field) {
            case TERM:
                if (value == null) {
                    unsetTerm();
                } else {
                    setTerm((java.lang.Long)value);
                }
                break;

            case VOTE_GRANTED:
                if (value == null) {
                    unsetVoteGranted();
                } else {
                    setVoteGranted((java.lang.Boolean)value);
                }
                break;

        }
    }

    @org.apache.thrift.annotation.Nullable
    public java.lang.Object getFieldValue(_Fields field) {
        switch (field) {
            case TERM:
                return getTerm();

            case VOTE_GRANTED:
                return isVoteGranted();

        }
        throw new java.lang.IllegalStateException();
    }

    /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
    public boolean isSet(_Fields field) {
        if (field == null) {
            throw new java.lang.IllegalArgumentException();
        }

        switch (field) {
            case TERM:
                return isSetTerm();
            case VOTE_GRANTED:
                return isSetVoteGranted();
        }
        throw new java.lang.IllegalStateException();
    }

    @Override
    public boolean equals(java.lang.Object that) {
        if (that instanceof RequestVoteResponse)
            return this.equals((RequestVoteResponse)that);
        return false;
    }

    public boolean equals(RequestVoteResponse that) {
        if (that == null)
            return false;
        if (this == that)
            return true;

        boolean this_present_term = true;
        boolean that_present_term = true;
        if (this_present_term || that_present_term) {
            if (!(this_present_term && that_present_term))
                return false;
            if (this.term != that.term)
                return false;
        }

        boolean this_present_voteGranted = true;
        boolean that_present_voteGranted = true;
        if (this_present_voteGranted || that_present_voteGranted) {
            if (!(this_present_voteGranted && that_present_voteGranted))
                return false;
            if (this.voteGranted != that.voteGranted)
                return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;

        hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(term);

        hashCode = hashCode * 8191 + ((voteGranted) ? 131071 : 524287);

        return hashCode;
    }

    @Override
    public int compareTo(RequestVoteResponse other) {
        if (!getClass().equals(other.getClass())) {
            return getClass().getName().compareTo(other.getClass().getName());
        }

        int lastComparison = 0;

        lastComparison = java.lang.Boolean.compare(isSetTerm(), other.isSetTerm());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetTerm()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.term, other.term);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        lastComparison = java.lang.Boolean.compare(isSetVoteGranted(), other.isSetVoteGranted());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetVoteGranted()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.voteGranted, other.voteGranted);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        return 0;
    }

    @org.apache.thrift.annotation.Nullable
    public _Fields fieldForId(int fieldId) {
        return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
        scheme(iprot).read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
        scheme(oprot).write(oprot, this);
    }

    @Override
    public java.lang.String toString() {
        java.lang.StringBuilder sb = new java.lang.StringBuilder("RequestVoteResponse(");
        boolean first = true;

        sb.append("term:");
        sb.append(this.term);
        first = false;
        if (!first) sb.append(", ");
        sb.append("voteGranted:");
        sb.append(this.voteGranted);
        first = false;
        sb.append(")");
        return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
        // check for required fields
        // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        try {
            write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
        } catch (org.apache.thrift.TException te) {
            throw new java.io.IOException(te);
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
        try {
            // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
            __isset_bitfield = 0;
            read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
        } catch (org.apache.thrift.TException te) {
            throw new java.io.IOException(te);
        }
    }

    private static class RequestVoteResponseStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public RequestVoteResponseStandardScheme getScheme() {
            return new RequestVoteResponseStandardScheme();
        }
    }

    private static class RequestVoteResponseStandardScheme extends org.apache.thrift.scheme.StandardScheme<RequestVoteResponse> {

        public void read(org.apache.thrift.protocol.TProtocol iprot, RequestVoteResponse struct) throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TField schemeField;
            iprot.readStructBegin();
            while (true)
            {
                schemeField = iprot.readFieldBegin();
                if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
                    break;
                }
                switch (schemeField.id) {
                    case 1: // TERM
                        if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
                            struct.term = iprot.readI64();
                            struct.setTermIsSet(true);
                        } else {
                            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                        }
                        break;
                    case 2: // VOTE_GRANTED
                        if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
                            struct.voteGranted = iprot.readBool();
                            struct.setVoteGrantedIsSet(true);
                        } else {
                            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                        }
                        break;
                    default:
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                }
                iprot.readFieldEnd();
            }
            iprot.readStructEnd();

            // check for required fields of primitive type, which can't be checked in the validate method
            struct.validate();
        }

        public void write(org.apache.thrift.protocol.TProtocol oprot, RequestVoteResponse struct) throws org.apache.thrift.TException {
            struct.validate();

            oprot.writeStructBegin(STRUCT_DESC);
            oprot.writeFieldBegin(TERM_FIELD_DESC);
            oprot.writeI64(struct.term);
            oprot.writeFieldEnd();
            oprot.writeFieldBegin(VOTE_GRANTED_FIELD_DESC);
            oprot.writeBool(struct.voteGranted);
            oprot.writeFieldEnd();
            oprot.writeFieldStop();
            oprot.writeStructEnd();
        }

    }

    private static class RequestVoteResponseTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public RequestVoteResponseTupleScheme getScheme() {
            return new RequestVoteResponseTupleScheme();
        }
    }

    private static class RequestVoteResponseTupleScheme extends org.apache.thrift.scheme.TupleScheme<RequestVoteResponse> {

        @Override
        public void write(org.apache.thrift.protocol.TProtocol prot, RequestVoteResponse struct) throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            java.util.BitSet optionals = new java.util.BitSet();
            if (struct.isSetTerm()) {
                optionals.set(0);
            }
            if (struct.isSetVoteGranted()) {
                optionals.set(1);
            }
            oprot.writeBitSet(optionals, 2);
            if (struct.isSetTerm()) {
                oprot.writeI64(struct.term);
            }
            if (struct.isSetVoteGranted()) {
                oprot.writeBool(struct.voteGranted);
            }
        }

        @Override
        public void read(org.apache.thrift.protocol.TProtocol prot, RequestVoteResponse struct) throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            java.util.BitSet incoming = iprot.readBitSet(2);
            if (incoming.get(0)) {
                struct.term = iprot.readI64();
                struct.setTermIsSet(true);
            }
            if (incoming.get(1)) {
                struct.voteGranted = iprot.readBool();
                struct.setVoteGrantedIsSet(true);
            }
        }
    }

    private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
        return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
    }
}

