import bowerick.*;

class ExampleJavaConsumer implements JmsConsumerCallback {
    public void processData(Object data, Object messageReferenceOrHeader) {
        System.out.println("Example Java consumer: " + String.valueOf(data));
    }
}

