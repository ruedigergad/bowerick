import bowerick.*;

class HelloWorldMessageGenerator implements MessageGenerator {
    public void generateMessage(JmsProducer producer) {
        producer.sendData("Hello World from Java");
    }
}

