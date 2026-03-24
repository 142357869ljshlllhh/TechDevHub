package com.techdevhub.result;

import com.techdevhub.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Integer code;
    private String message;
    private Object data;
    public static Result success(){
        return new Result(200,"success",null);
    }
    public static Result success(Object data){
        return new Result(200,"success",data);
    }
    public static Result fail(ErrorCode errorCode){
        return new Result(errorCode.getCode(), errorCode.getMessage(), null);
    }
}
