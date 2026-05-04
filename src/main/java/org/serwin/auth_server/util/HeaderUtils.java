package org.serwin.auth_server.util;
import java.util.Enumeration;

import org.hibernate.annotations.Comment;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class HeaderUtils {

    public static String getHeader(String name) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null) return null;

        HttpServletRequest request = attrs.getRequest();

        String value = request.getHeader(name);

        log.info("Header [{}] = {}", name, value);



        
        return value;
    }
}