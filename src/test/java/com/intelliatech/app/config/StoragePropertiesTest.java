package com.intelliatech.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoragePropertiesTest {

    @Test
    void s3IsTheConfiguredAndImplicitDefaultProvider() {
        assertThat(new StorageProperties("s3").usesS3()).isTrue();
        assertThat(new StorageProperties(null).usesS3()).isTrue();
        assertThat(new StorageProperties("").usesS3()).isTrue();
        assertThat(new StorageProperties("local").usesS3()).isFalse();
    }
}
