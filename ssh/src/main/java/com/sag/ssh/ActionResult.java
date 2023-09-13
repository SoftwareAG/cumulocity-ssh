package com.sag.ssh;

import org.json.JSONPropertyIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class ActionResult {
    private int status = 0;
    private String result = "";
    private String error = "";

    @JSONPropertyIgnore
    public void addActionResult(ActionResult actionResult) {
        status += actionResult.getStatus();
        result += actionResult.getResult() + "\n";
        error += actionResult.getError() + "\n";
    }
}
