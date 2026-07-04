package com.example.collegeroitool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "model_response")
public class ModelResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_input_payload", nullable = false)
    private InputPayloadType typeInputPayload;

    @Lob
    @Column(name = "output_payload", columnDefinition = "TEXT")
    private String outputPayload;

    /** Row id in the table named by typeInputPayload. No DB FK — the target table varies by type. */
    @Column(name = "input_id")
    private Long inputId;

    @Column(name = "response_status")
    private Integer responseStatus;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                                    { return id; }
    public String getModelName()                           { return modelName; }
    public void   setModelName(String v)                   { this.modelName = v; }
    public InputPayloadType getTypeInputPayload()          { return typeInputPayload; }
    public void   setTypeInputPayload(InputPayloadType v)  { this.typeInputPayload = v; }
    public String getOutputPayload()                       { return outputPayload; }
    public void   setOutputPayload(String v)                { this.outputPayload = v; }
    public Long getInputId()                               { return inputId; }
    public void setInputId(Long v)                          { this.inputId = v; }
    public Integer getResponseStatus()                      { return responseStatus; }
    public void    setResponseStatus(Integer v)             { this.responseStatus = v; }
    public LocalDateTime getCreatedAt()                     { return createdAt; }
}
