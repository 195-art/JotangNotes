package org.example.jotangnote.common;

import lombok.Data;

/**
 * 接口统一返回结果
 */
@Data
public class Result {
    // 业务状态码，不代表所有接口的HTTP响应状态
    private int code;
    // 结果说明或错误提示
    private String message;
    // 返回给客户端的业务数据
    private Object data;

    /**
     * 封装成功结果
     *
     * @param data 业务数据
     * @return 业务状态码为200的结果
     */
    public static Result success(Object data) {
        var r = new Result();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    /**
     * 封装默认失败结果
     *
     * @param message 错误提示
     * @return 业务状态码为400的结果
     */
    public static Result fail(String message) {
        return fail(400, message);
    }

    /**
     * 封装指定状态码的失败结果
     *
     * @param code 业务状态码
     * @param message 错误提示
     * @return 失败结果
     */
    public static Result fail(int code, String message) {
        var r = new Result();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
