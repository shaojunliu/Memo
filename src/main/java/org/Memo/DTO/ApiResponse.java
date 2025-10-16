package org.Memo.DTO;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    /** "OK"/"FAIL" */
    private String resStatus;

    /** 业务码（200=成功，其它自定） */
    private Integer resCode;

    private String resMsg;

    /** 业务数据 */
    private T resBody;

    public static <T> ApiResponse<T> ok(T body) {
        return ApiResponse.<T>builder()
                .resStatus("SUCCESS")
                .resCode(200)
                .resBody(body)
                .resMsg("SUCCESS")
                .build();
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return ApiResponse.<T>builder()
                .resStatus("FAIL")
                .resCode(code)
                .resMsg(message)
                .resBody(null)
                .build();
    }
}
