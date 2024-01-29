package example.cosmos.extension;

/**
 * @author jiangyu.666@bytedance.com
 * @date 2024/1/29
 */
@Spi("foo")
public class Foo implements Food {
    @Override
    public void doSomething() {
        System.out.println("I am foo");

    }
}
