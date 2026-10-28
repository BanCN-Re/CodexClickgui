package wtf.carnation.manager.command;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CommandInfo {
    String name();
    String desc() default "";
    String[] usage() default {};
    String[] aliases() default {};
}
