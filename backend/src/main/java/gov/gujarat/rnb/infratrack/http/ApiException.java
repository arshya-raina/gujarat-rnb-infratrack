package gov.gujarat.rnb.infratrack.http;

/** Thrown by handlers to return an error status with a readable message: {"detail": "..."} */
public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String detail) {
        super(detail);
        this.status = status;
    }

    public static ApiException notFound(String what) { return new ApiException(404, what + " not found"); }
    public static ApiException forbidden(String why) { return new ApiException(403, why); }
    public static ApiException bad(String why) { return new ApiException(400, why); }
}
