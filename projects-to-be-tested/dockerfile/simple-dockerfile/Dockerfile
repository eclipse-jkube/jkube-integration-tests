#
# Copyright (c) 2019 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at:
#
#     https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#   Red Hat, Inc. - initial API and implementation
#

FROM openjdk:latest
COPY maven/target/simple-dockerfile-0.0.0-SNAPSHOT.jar /tmp/simple-dockerfile-0.0.0-SNAPSHOT.jar
COPY maven/static-dir-in-project-root/my-file.txt /tmp/my-file.txt
CMD ["java", "-jar", "/tmp/simple-dockerfile-0.0.0-SNAPSHOT.jar"]
