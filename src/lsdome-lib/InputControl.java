import java.io.*;
import java.util.*;

enum ControlType {
    BUTTON,
    SLIDER,
    JOG
}

public class InputControl {

    BufferedReader input;
    Map<String, InputHandler> handlers;

    static class InputHandler {
        void button(boolean pressed) {
            throw new RuntimeException("handler did not override!");
        }

        void slider(double val) {
            throw new RuntimeException("handler did not override!");
        }

        void jog(boolean inc) {
            throw new RuntimeException("handler did not override!");
        }
    }

    void init() {
        try {
            input = new BufferedReader(new FileReader(Config.MIDI_SOCKET));
        } catch (IOException ioe) {
            System.err.println("Couldn't load midi socket");
        }
        handlers = new HashMap<String, InputHandler>();
    }

    void registerHandler(String controlName, InputHandler handler) {
        handlers.put(controlName, handler);
    }

    void processInput() {
        if (input == null) {
            return;
        }

        try {
            while (input.ready()) {
                String line = input.readLine();
                if (Config.DEBUG) {
                    System.out.println(line);
                }
                processInputEvent(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void processInputEvent(String event) {
        String[] parts = event.split(" ");
        String name = parts[0];
        String evt = parts[1];
        InputHandler handler = handlers.get(name);
        if (handler == null) {
            return;
        }

        ControlType type;
        boolean boolVal = false;
        double realVal = -1;
        
        if (evt.equals("press")) {
            type = ControlType.BUTTON;
            boolVal = true;
        } else if (evt.equals("release")) {
            type = ControlType.BUTTON;
            boolVal = false;
        } else if (evt.equals("inc")) {
            type = ControlType.JOG;
            boolVal = true;
        } else if (evt.equals("dec")) {
            type = ControlType.JOG;
            boolVal = false;
        } else {
            type = ControlType.SLIDER;
            realVal = Integer.parseInt(evt) / 127.;
            if (realVal < 0. || realVal > 1.) {
                System.err.println("slider out of range " + realVal);
            }
        }

        switch (type) {
        case BUTTON:
            handler.button(boolVal);
            break;
        case SLIDER:
            handler.slider(realVal);
            break;
        case JOG:
            handler.jog(boolVal);
            break;
        }
    }
}