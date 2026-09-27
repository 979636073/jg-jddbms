package com.jd.biz.domain.core.impl;

import com.jd.common.config.HzbConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class DatabaseServiceImplDmpTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldUseConfiguredProfileAsDmpWorkingDirectory() throws Exception {
        String originalProfile = HzbConfig.getProfile();
        try {
            File profileDirectory = tempFolder.newFolder("profile");
            new HzbConfig().setProfile(profileDirectory.getAbsolutePath());

            File workingDirectory = DatabaseServiceImpl.dmpWorkingDirectory();

            assertEquals(profileDirectory.getCanonicalFile(), workingDirectory.getCanonicalFile());
        } finally {
            new HzbConfig().setProfile(originalProfile);
        }
    }
}
