/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.integrationtests.quarkus.rest;

import io.fabric8.openshift.api.model.ImageStream;
import org.apache.maven.shared.invoker.InvocationResult;
import org.eclipse.jkube.integrationtests.OpenShiftCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;

import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT_OSCI;
import static org.eclipse.jkube.integrationtests.assertions.InvocationResultAssertion.assertInvocation;
import static org.eclipse.jkube.integrationtests.assertions.JKubeAssertions.assertJKube;
import static org.eclipse.jkube.integrationtests.assertions.KubernetesListAssertion.assertListResource;
import static org.eclipse.jkube.integrationtests.assertions.YamlAssertion.yaml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@Tag(OPEN_SHIFT_OSCI)
@TestMethodOrder(OrderAnnotation.class)
class QuarkusOcITCase extends Quarkus implements OpenShiftCase {

  @Test
  @Order(1)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:build, should create image")
  void ocBuild() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:build");
    // Then
    assertInvocation(invocationResult);
    final ImageStream is = getOpenShiftClient().imageStreams().withName(getApplication()).get();
    assertThat(is, notNullValue());
    assertThat(is.getStatus().getTags().iterator().next().getTag(), equalTo("latest"));
  }

  @Test
  @Order(1)
  @DisplayName("oc:resource, should create manifests")
  void ocResource() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:resource");
    // Then
    assertInvocation(invocationResult);
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", getProject()));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertListResource(new File(metaInfDirectory, "jkube/openshift.yml"));
    assertThat(new File(metaInfDirectory, "jkube/openshift/quarkus-rest-deploymentconfig.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/openshift/quarkus-rest-route.yml"), yaml(not(anEmptyMap())));
    assertThat(new File(metaInfDirectory, "jkube/openshift/quarkus-rest-service.yml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(2)
  @DisplayName("oc:helm, should create Helm charts")
  void ocHelm() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:helm");
    // Then
    assertInvocation(invocationResult);
    final File helmDirectory = new File(
      String.format("../%s/target/jkube/helm/%s/openshift", getProject(), getApplication()));
    assertHelm(helmDirectory);
    assertThat(new File(helmDirectory, "templates/quarkus-rest-deploymentconfig.yaml"), yaml(not(anEmptyMap())));
    assertThat(new File(helmDirectory, "templates/quarkus-rest-route.yaml"), yaml(not(anEmptyMap())));
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:apply, should deploy pod and service")
  void ocApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:apply");
    // Then
    assertInvocation(invocationResult);
    assertThatShouldApplyResources();
  }

  @Test
  @Order(4)
  @DisplayName("oc:undeploy, should delete all applied resources")
  void ocUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:undeploy");
    // Then
    assertInvocation(invocationResult);
    assertJKube(this)
      .assertThatShouldDeleteAllAppliedResources();
    cleanUpCluster();
  }
}
