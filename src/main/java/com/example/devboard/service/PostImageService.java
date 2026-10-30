package com.example.devboard.service;

import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostImage;
import com.example.devboard.repository.PostImageRepository;
import com.example.devboard.repository.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PostImageService {

    private final PostImageRepository postImageRepository;
    private final PostRepository postRepository;
    private final ImageStorageService imageStorageService;

    public PostImageService(
            PostImageRepository postImageRepository,
            PostRepository postRepository,
            ImageStorageService imageStorageService
    ) {
        this.postImageRepository = postImageRepository;
        this.postRepository = postRepository;
        this.imageStorageService = imageStorageService;
    }

    // 게시글에 이미지 여러 장 등록
    @Transactional
    public List<PostImage> uploadImages(
            Long postId,
            List<MultipartFile> files,
            String email
    ) {
        // 업로드 파일 목록 확인
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "업로드할 이미지가 없습니다."
            );
        }

        // 빈 파일이 포함된 요청은 거부
        if (files.stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비어 있는 이미지 파일이 포함되어 있습니다."
            );
        }

        // 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "게시글을 찾을 수 없습니다."
                ));

        // 게시글 작성자만 이미지를 추가할 수 있도록 검증
        if (post.getMember() == null
                || !post.getMember().getEmail().equals(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 작성한 게시글에만 이미지를 추가할 수 있습니다."
            );
        }

        // 게시글당 이미지 최대 5장 제한
        long existingCount = postImageRepository.countByPostId(postId);

        if (existingCount + files.size() > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "게시글에는 이미지를 최대 5장까지 등록할 수 있습니다."
            );
        }

        List<PostImage> savedImages = new ArrayList<>();
        List<String> storedPaths = new ArrayList<>();

        try {
            // 파일을 저장하고 게시글과 연결
            for (MultipartFile file : files) {

                // 실제 이미지 파일 저장
                String imagePath = imageStorageService.storeImage(file);
                storedPaths.add(imagePath);

                // 원본 파일명 확인
                String originalFilename = file.getOriginalFilename();

                if (originalFilename == null || originalFilename.isBlank()) {
                    originalFilename = "image";
                }

                // 이미지 정보 생성
                PostImage postImage = new PostImage(
                        imagePath,
                        originalFilename,
                        post
                );

                // DB에 이미지 정보 저장
                savedImages.add(postImageRepository.save(postImage));
            }

            return savedImages;

        } catch (RuntimeException e) {
            // 업로드 실패 시 이번 요청에서 저장한 파일 정리
            for (String path : storedPaths) {
                try {
                    imageStorageService.deleteImage(path);
                } catch (RuntimeException cleanupException) {
                    // 정리 오류가 원래 업로드 오류를 덮어쓰지 않도록 처리
                }
            }

            throw e;
        }
    }

    // 게시글의 이미지 목록 조회
    public List<PostImage> findImagesByPostId(Long postId) {

        // 게시글 존재 여부 확인
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }

        // 이미지 ID 순서대로 반환
        return postImageRepository.findByPostIdOrderByIdAsc(postId);
    }

    // 게시글에 등록된 이미지 한 장 삭제
    @Transactional
    public void deleteImage(Long postId, Long imageId, String email) {

        // 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "게시글을 찾을 수 없습니다."
                ));

        // 게시글 작성자만 이미지를 삭제할 수 있도록 검증
        if (post.getMember() == null
                || !post.getMember().getEmail().equals(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 작성한 게시글의 이미지만 삭제할 수 있습니다."
            );
        }

        // 이미지 조회
        PostImage image = postImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "이미지를 찾을 수 없습니다."
                ));

        // 해당 이미지가 요청한 게시글에 속하는지 확인
        if (image.getPost() == null
                || !image.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 게시글에 등록된 이미지가 아닙니다."
            );
        }

        // 서버에 저장된 실제 이미지 파일 삭제
        imageStorageService.deleteImage(image.getImagePath());

        // 데이터베이스에 저장된 이미지 정보 삭제
        postImageRepository.delete(image);
    }
}