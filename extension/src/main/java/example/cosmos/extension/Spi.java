package example.cosmos.extension;

import java.lang.annotation.*;

/**
 * @author jiangyu.666@bytedance.com
 * @date 2024/1/29
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Spi {

    String value();


}
