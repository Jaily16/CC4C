package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BusinessCacheValueCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final BusinessCacheValueCodec codec = new BusinessCacheValueCodec(objectMapper);
    private final JavaType valueType = objectMapper.getTypeFactory().constructType(Value.class);

    @Test
    void roundTripsValueAndNegativeEntryWithStableEnvelope() throws Exception {
        String valueJson = codec.encode(Optional.of(new Value(7, "course-a")));
        BusinessCacheValueCodec.DecodedValue<Value> value = codec.decode(valueJson, valueType);
        BusinessCacheValueCodec.DecodedValue<Value> negative = codec.decode(codec.encode(Optional.empty()), valueType);

        assertEquals(BusinessCacheValueCodec.DecodedValue.State.VALUE, value.state());
        assertEquals(new Value(7, "course-a"), value.value());
        assertEquals(BusinessCacheValueCodec.DecodedValue.State.NEGATIVE, negative.state());
    }

    @Test
    void rejectsUnsupportedOrMissingEnvelopeValues() {
        assertThrows(Exception.class, () -> codec.decode("{\"schemaVersion\":2}", valueType));
        assertThrows(Exception.class, () -> codec.decode("{\"schemaVersion\":1,\"negative\":false}", valueType));
    }

    private record Value(int id, String name) {}
}
