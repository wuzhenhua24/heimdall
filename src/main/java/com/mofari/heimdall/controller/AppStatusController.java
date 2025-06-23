package com.mofari.heimdall.controller;

import com.mofari.heimdall.service.AppStatusStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/v1") // 统一的 API 前缀
public class AppStatusController {

    private final AppStatusStore appStatusStore;

    @Autowired
    public AppStatusController(AppStatusStore appStatusStore) {
        this.appStatusStore = appStatusStore;
    }

    /**
     * 提供一个获取所有应用当前状态快照的 HTTP GET 接口。
     * @return 所有应用状态的列表
     */
    @GetMapping("/status/all")
    public Collection<Map<String, Object>> getAllStatuses() {
        return appStatusStore.getAllStatuses();
    }
}