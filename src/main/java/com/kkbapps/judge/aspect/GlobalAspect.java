package com.kkbapps.judge.aspect;

import com.kkbapps.judge.annotation.GlobalInterceptor;
import com.kkbapps.judge.annotation.VerifyParam;
import com.kkbapps.judge.exception.BusinessException;
import com.kkbapps.judge.pojo.Result;
import com.kkbapps.judge.utils.AuthorizationUtil;
import com.kkbapps.judge.utils.ReUtil;
import com.kkbapps.judge.utils.StringUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;


/**
 * 全局AOP拦截
 */
@Aspect
@Component
public class GlobalAspect {

    /**
     * 常见参数类型
     */
    private static final String[] TYPE_BASE = {"java.lang.String", "java.lang.Integer", "java.lang.Long"};

    /**
     * 定义切入点
     */
    @Pointcut("@annotation(com.kkbapps.judge.annotation.GlobalInterceptor)")
    private void requestInterceptor() {
    }

    /**
     * 校验参数
     */
    @Around("requestInterceptor()")
    public Object DoInterceptor(ProceedingJoinPoint point) throws Throwable {
        Object target = point.getTarget();
        Object[] args = point.getArgs();
        String methodName = point.getSignature().getName();
        Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        GlobalInterceptor globalInterceptor = method.getAnnotation(GlobalInterceptor.class);

        if(null == globalInterceptor) return null;
        // 参数校验
        if(globalInterceptor.checkParams()) verifyParams(method, args);
        // 权限校验
        if(globalInterceptor.checkAuth()) verifyAuth();
        return point.proceed();
    }

    // 校验参数
    private void verifyParams(Method method, Object[] args){
        Parameter[] parameters = method.getParameters();
        for(int i=0;i<parameters.length;i++)
        {
            Parameter parameter = parameters[i];
            Object value = args[i];
            VerifyParam verifyParam = parameter.getAnnotation(VerifyParam.class);

            if(null == verifyParam) continue;

            // 基本类型
            if(ArrayUtils.contains(TYPE_BASE, parameter.getParameterizedType().getTypeName())){
                checkBaseParam(verifyParam, value,parameter.getParameterizedType().getTypeName());
            }
            // 对象
            else{
                checkObjParam(value);
            }
        }
    }

    // 校验基本类型参数
    private void checkBaseParam(VerifyParam verifyParam, Object value, String type){
        boolean isEmpty = value == null || StringUtil.isEmpty(value.toString());
        int length = value == null ? 0 : value.toString().length();

        // 校验必需
        if(isEmpty && verifyParam.required()){
            throw new BusinessException(Result.error(400,"参数为空！"));
        }

        // 校验长度
        if(!isEmpty && !(length >= verifyParam.minLen() && length <= verifyParam.maxLen())){
            throw new BusinessException(Result.error(400,"参数长度错误！"));
        }

        // 校验值
        if(!TYPE_BASE[0].equals(type)) {
            long val = (Long) value;
            if(!isEmpty && !(val >= verifyParam.minVal() && val <= verifyParam.maxVal())){
                throw new BusinessException(Result.error(400,"参数值错误！"));
            }
        }

        // 校验正则
        if(!isEmpty && !"".equals(verifyParam.re().getRegex()) &&
                !ReUtil.verifyRe(verifyParam.re(),value.toString())){
            throw new BusinessException(Result.error(400,"参数格式不正确！"));
        }

    }

    // 校验对象参数
    private void checkObjParam(Object obj) {
        // 校验必需
        if(null == obj){
            throw new BusinessException(Result.error(400,"参数为空！"));
        }
        Class<?> clazz = obj.getClass();

        // 获取类的所有字段
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            // 设置字段为可访问，即使是 private 的字段也可以访问
            field.setAccessible(true);

            try {
                // 获取字段上的注解
                VerifyParam verifyParam = field.getAnnotation(VerifyParam.class);
                if(null == verifyParam) continue;

                // 获取字段的类型
                Class<?> fieldType = field.getType();

                // 获取字段的值
                Object value = field.get(obj);

                // 基本类型
                if(ArrayUtils.contains(TYPE_BASE, fieldType.getName())){
                    checkBaseParam(verifyParam, value, fieldType.getName());
                }
                // 对象
                else checkObjParam(value);

                // 打印字段名、类型和值
                // System.out.println("Field: " + field.getName() + ", Type: " + fieldType.getName() + ", Value: " + value);
                // Field: userName, Type: java.lang.String, Value: kk

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    // 校验权限
    private void verifyAuth() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String Authorization = request.getHeader("Authorization");
        if(Authorization == null) throw new BusinessException(Result.error(400,"token为空！"));
        String[] keys = Authorization.split(" ");
        if(keys.length != 3 || !"Bearer".equals(keys[0])) throw new BusinessException(Result.error(400,"token格式错误！"));
        String checkInfo = AuthorizationUtil.checkKey(keys[1],keys[2]);
        if(!AuthorizationUtil.AccessKeyValidInfo.equals(checkInfo))
            throw new BusinessException(Result.error(400,checkInfo));
    }
}