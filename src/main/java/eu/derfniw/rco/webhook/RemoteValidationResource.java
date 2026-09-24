/*
 * Copyright 2026.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.derfniw.rco.webhook;

import eu.derfniw.rco.api.v1alpha1.RCloneClusterRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.remote.FieldError;
import eu.derfniw.rco.remote.RemoteSpecValidator;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.StatusCause;
import io.fabric8.kubernetes.api.model.StatusCauseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validating admission webhooks for RCloneRemote and RCloneClusterRemote, registered for CREATE and UPDATE. They run
 * the checks that the CRD schema and its CEL rules cannot express.
 */
@Path("/")
public class RemoteValidationResource {

    private final KubernetesSerialization serialization;

    public RemoteValidationResource(KubernetesClient client) {
        this.serialization = client.getKubernetesSerialization();
    }

    @POST
    @Path("validate-rco-frozenbits-se-v1alpha1-rcloneremote")
    public AdmissionReview validateRemote(AdmissionReview review) {
        return validate(review, RCloneRemote.class);
    }

    @POST
    @Path("validate-rco-frozenbits-se-v1alpha1-rcloneclusterremote")
    public AdmissionReview validateClusterRemote(AdmissionReview review) {
        return validate(review, RCloneClusterRemote.class);
    }

    private AdmissionReview validate(
            AdmissionReview review, Class<? extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>> type) {
        var request = review.getRequest();
        var remote = serialization.convertValue(request.getObject(), type);
        var errors = RemoteSpecValidator.validate(remote.getSpec());

        var response = errors.isEmpty()
                ? new AdmissionResponseBuilder().withAllowed(true)
                : denied(remote.getKind(), remote.getMetadata().getName(), errors);
        return new AdmissionReviewBuilder()
                .withApiVersion(review.getApiVersion())
                .withKind(review.getKind())
                .withResponse(response.withUid(request.getUid()).build())
                .build();
    }

    /** Builds a response equivalent to apimachinery's {@code apierrors.NewInvalid}. */
    private static AdmissionResponseBuilder denied(String kind, String name, List<FieldError> errors) {
        var causes = errors.stream().map(RemoteValidationResource::cause).toList();
        var message = "%s.%s \"%s\" is invalid: %s"
                .formatted(
                        kind,
                        RCloneRemote.GROUP,
                        name,
                        errors.stream().map(FieldError::toString).collect(Collectors.joining(", ")));
        return new AdmissionResponseBuilder()
                .withAllowed(false)
                .withStatus(new StatusBuilder()
                        .withStatus("Failure")
                        .withCode(422)
                        .withReason("Invalid")
                        .withMessage(message)
                        .withNewDetails()
                        .withGroup(RCloneRemote.GROUP)
                        .withKind(kind)
                        .withName(name)
                        .withCauses(causes)
                        .endDetails()
                        .build());
    }

    private static StatusCause cause(FieldError error) {
        var reason =
                switch (error.type()) {
                    case REQUIRED -> "FieldValueRequired";
                    case INVALID -> "FieldValueInvalid";
                    case NOT_SUPPORTED -> "FieldValueNotSupported";
                };
        return new StatusCauseBuilder()
                .withReason(reason)
                .withField(error.field())
                .withMessage(error.toString())
                .build();
    }
}
