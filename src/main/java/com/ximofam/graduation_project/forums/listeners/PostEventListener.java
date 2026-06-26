package com.ximofam.graduation_project.forums.listeners;

import com.ximofam.graduation_project.forums.dtos.response.ModerationResponse;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.forums.events.PostModerationEvent;
import com.ximofam.graduation_project.forums.repositories.PostRepository;
import com.ximofam.graduation_project.forums.repositories.projection.PostContentProjection;
import com.ximofam.graduation_project.forums.services.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(
        queues = "${app.rabbitmq.queues.post.name}",
        concurrency = "2-5",
        containerFactory = "rabbitListenerContainerFactory"
)
public class PostEventListener {
    private final PostRepository postRepository;
    private final PostService postService;
    private final ChatClient chatClient;

    @Value("classpath:prompts/check_post.st")
    private Resource checkPostPrompt;

    @RabbitHandler
    public void handleModeration(PostModerationEvent event) {
        log.info("Bắt đầu kiểm duyệt bài viết tự động cho PostId: {}", event.getPostId());

        PostContentProjection postTitleAndContent = postRepository.findTitleAndContentById(event.getPostId()).orElse(null);
        if (postTitleAndContent == null) {
            log.error("Hủy bỏ xử lý: PostId {} không tồn tại trong hệ thống. Tin nhắn được ACK tự động để giải phóng hàng đợi.", event.getPostId());
            return;
        }

        try {
            ModerationResponse res = chatClient.prompt()
                    .system(checkPostPrompt)
                    .user(u -> u.text("Bài đăng cần kiểm duyệt: '''{content}'''")
                            .param("content", postTitleAndContent.getTitle() + "\n" + postTitleAndContent.getContent()))
                    .call()
                    .entity(ModerationResponse.class);

            if (res == null || res.getStatus() == null) {
                throw new IllegalStateException("AI không trả về kết quả kiểm duyệt hợp lệ hoặc sai cấu trúc.");
            }

            PostStatus targetStatus = PostStatus.valueOf(res.getStatus().trim().toUpperCase(Locale.ROOT));
            postService.updateModerationPost(event.getPostId(), targetStatus, res.getReason());

            log.info("Kiểm duyệt thành công PostId: {}. Trạng thái mới: {}", event.getPostId(), targetStatus);
        } catch (Exception e) {
            log.error("Lỗi xảy ra trong quá trình gọi AI hoặc xử lý kết quả cho PostId: {}. Lỗi: {}", event.getPostId(), e.getMessage());
            throw e;
        }
    }
}