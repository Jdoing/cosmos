package example.cosmos.extension;

/**
 * @author jiangyu.666@bytedance.com
 * @date 2024/1/29
 */
@Spi("bar")
public class Bar implements Food{
    @Override
    public void doSomething() {
        System.out.println("I am bar");
    }
}
