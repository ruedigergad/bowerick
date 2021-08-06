import bowerick.*;

/*
 * Compile with, e.g.:
 * javac -cp ../dist/bowerick-2.8.0-standalone.jar HelloWorldMessageGenerator.java
 *
 * Run with, e.g.:
 * java -jar ../dist/bowerick-2.8.0-standalone.jar -I 1000 -G custom-fn -X ./HelloWorldMessageGenerator.class
 */
class HelloWorldMessageGenerator implements MessageGenerator {
    public void generateMessage(JmsProducer producer) {
        producer.sendData("Hello World from Java");
    }
}

