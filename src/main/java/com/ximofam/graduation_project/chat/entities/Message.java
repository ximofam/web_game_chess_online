//package com.ximofam.graduation_project.chat.entities;
//
//import com.ximofam.graduation_project.common.helpers.models.BaseModel;
//import com.ximofam.graduation_project.users.entities.User;
//import jakarta.persistence.*;
//import lombok.Getter;
//import lombok.Setter;
//
//@Entity
//@Table(name = "messages")
//@Getter
//@Setter
//public class Message extends BaseModel {
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "conversation_id", nullable = false)
//    private Conversation conversation;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "sender_id", nullable = false)
//    private User sender;
//
//    @Column(name = "content")
//    private String content;
//}