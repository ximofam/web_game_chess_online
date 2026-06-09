package com.ximofam.graduation_project.users.repositories;

import com.ximofam.graduation_project.users.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
