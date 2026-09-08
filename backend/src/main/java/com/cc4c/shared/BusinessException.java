package com.cc4c.shared;

import org.springframework.http.HttpStatus;

/**
 * 表示共享基础设施处理中可分类且可安全映射的失败。
 */
public final class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final int code;
    private final Object data;

    /**
     * 创建 BusinessException 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param status 当前对象或流程的有限状态
     * @param code 调用方提供的 {@code code} 值
     * @param message 当前处理的消息或用户提示
     */
    public BusinessException(HttpStatus status, BusinessCode code, String message) {
        this(status, code.code(), false, message);
    }

    /**
     * 创建 BusinessException 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param status 当前对象或流程的有限状态
     * @param code 调用方提供的 {@code code} 值
     * @param data 调用方提供的 {@code data} 值
     * @param message 当前处理的消息或用户提示
     */
    public BusinessException(HttpStatus status, int code, Object data, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 当前操作产生的 HttpStatus 结果
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前规则计算或读取的数值
     */
    public int code() {
        return code;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 当前操作产生的 Object 结果
     */
    public Object data() {
        return data;
    }
}
