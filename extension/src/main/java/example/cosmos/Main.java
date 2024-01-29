package example.cosmos;

import example.cosmos.extension.ExtensionLoader;
import example.cosmos.extension.Foo;
import example.cosmos.extension.Food;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Food bar = ExtensionLoader.load(Food.class).getExtension("bar");

        bar.doSomething();
    }
}