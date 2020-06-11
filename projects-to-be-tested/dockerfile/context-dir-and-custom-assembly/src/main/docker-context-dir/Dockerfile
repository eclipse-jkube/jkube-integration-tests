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

FROM openjdk:11-jre-slim
ADD input-files/subdirectory-in-context /deployments/subdirectory
COPY input-files/file-in-context.txt /deployments/file-in-context.txt
ENTRYPOINT [ "echo READY PLAYER 1 && sleep 3600" ]
