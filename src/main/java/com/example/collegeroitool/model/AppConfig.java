package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_config")
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", unique = true, nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId()                     { return id; }
    public String getConfigKey()            { return configKey; }
    public void   setConfigKey(String v)    { this.configKey = v; }
    public String getConfigValue()          { return configValue; }
    public void   setConfigValue(String v)  { this.configValue = v; }
    public LocalDateTime getUpdatedAt()     { return updatedAt; }
}
