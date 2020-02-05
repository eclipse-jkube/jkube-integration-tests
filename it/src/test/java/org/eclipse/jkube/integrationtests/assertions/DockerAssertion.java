package org.eclipse.jkube.integrationtests.assertions;

import org.eclipse.jkube.integrationtests.docker.DockerUtils;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

public class DockerAssertion {

  public static void assertImageWasRecentlyBuilt(String repository, String name) throws IOException, InterruptedException {
    final List<DockerUtils.DockerImage> dockerImages = DockerUtils.dockerImages();
    assertThat(dockerImages, hasSize(greaterThanOrEqualTo(1)));
    final DockerUtils.DockerImage mostRecentImage = dockerImages.stream()
      .filter(di -> di.getRepository().contains(name)).findFirst().orElse(null);
    assertThat(mostRecentImage, notNullValue());
    assertThat(mostRecentImage.getRepository(), equalTo(String.format("%s/%s",repository, name)));
    assertThat(mostRecentImage.getTag(), equalTo("latest"));
    assertThat(mostRecentImage.getCreatedSince(), containsString("second"));
  }
}
