package com.example.devboard.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "post_likes",
        uniqueConstraints = {
                // 회원과 게시글의 조합이 중복되지 않도록 DB에서 제한한다.
                // 따라서 같은 회원이 같은 게시글에 좋아요를 여러 번 저장할 수 없다.
        @UniqueConstraint(name = "uk_post_like_member_post", columnNames = {"member_id", "post_id"})},
        indexes = {
        @Index(name = "idx_post_likes_post_id", columnList = "post_id"),
        @Index(name = "idx_post_likes_member_id", columnList = "member_id")})
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PostLike() {}

    public PostLike(Member member, Post post) {
        this.member = member;
        this.post = post;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Post getPost() {
        return post;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
