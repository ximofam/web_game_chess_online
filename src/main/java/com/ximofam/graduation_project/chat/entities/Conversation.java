//package com.ximofam.graduation_project.chat.entities;
//
//import com.ximofam.graduation_project.chat.entities.enums.ConversationType;
//import com.ximofam.graduation_project.common.helpers.models.BaseModel;
//import jakarta.persistence.*;
//import lombok.Getter;
//import lombok.Setter;
//import org.hibernate.annotations.BatchSize;
//
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//
//@Entity
//@Table(name = "conversations")
//@Getter
//@Setter
//public class Conversation extends BaseModel {
//    @Enumerated(EnumType.STRING)
//    @Column(name = "type")
//    private ConversationType type;
//
//    @Column(name = "last_message_at")
//    private Instant lastMessageAt = Instant.now();
//
//    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
//    @OrderBy("id DESC")
//    @BatchSize(size = 20)
//    private List<Message> messages = new ArrayList<>();
//}
