package org.Memo.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoController {
    @GetMapping("/memo")
    public String home() {
        return "HEALTH";
    }
}
