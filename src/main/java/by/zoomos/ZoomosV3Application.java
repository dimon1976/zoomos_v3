package by.zoomos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Основной класс приложения ZoomosV3Application
 *
 * @author Your Name
 * @version 1.0
 */
@SpringBootApplication
@EnableAsync
@EnableTransactionManagement
public class ZoomosV3Application {


    /**
     * Точка входа в приложение
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(ZoomosV3Application.class, args);
    }

}
