package com.intelliatech.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class S3StoragePropertiesTest {

    @Test
    void bucketAutomaticallyActivatesS3ForIamRoleDeployments() {
        var properties = new S3StorageProperties(false, "company-files", "ap-south-1",
                "", "", "intelliatech-books", "");

        assertThat(properties.active()).isTrue();
        assertThat(properties.complete()).isTrue();
    }

    @Test
    void missingBucketDoesNotSelectS3() {
        var properties = new S3StorageProperties(false, "", "ap-south-1",
                "", "", "intelliatech-books", "");

        assertThat(properties.active()).isFalse();
        assertThat(properties.complete()).isFalse();
    }
}
