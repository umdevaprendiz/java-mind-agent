package com.example.java_mind_agent.common.web;

import java.net.URI;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.java_mind_agent.common.error.ApiException;
import com.example.java_mind_agent.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Todo erro da API que acontece antes de um stream começar vira {@code application/problem+json} (RFC 9457) com o
 * {@code code} estável do contrato ({@link ErrorCode}). Erros dentro de um stream SSE não passam por aqui: já saíram
 * com 200 e vão como evento {@code error}.
 *
 * <p>O {@code Content-Type} é fixado de propósito: um cliente que pediu {@code Accept: text/event-stream} (o download
 * de modelo, por exemplo) ainda precisa receber o erro, e sem isso o Spring responderia 406.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApi(ApiException e, HttpServletRequest request) {
		ProblemDetail problem = problem(e.getCode(), e.getMessage(), request.getRequestURI());
		problem.setProperty("retryable", e.isRetryable());
		e.getDetails().forEach(problem::setProperty);
		if (e.getCode() == ErrorCode.INTERNAL_ERROR) {
			log.error("Erro interno em {}", request.getRequestURI(), e);
		}
		return respond(problem);
	}

	/** Rede de segurança: nunca vaza stack trace nem mensagem interna para o cliente. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Erro inesperado em {}", request.getRequestURI(), e);
		return respond(problem(ErrorCode.INTERNAL_ERROR, "Erro inesperado. Veja o log do servidor para os detalhes.",
				request.getRequestURI()));
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<java.util.Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(f -> java.util.Map.of("field", f.getField(), "message",
					f.getDefaultMessage() == null ? "inválido" : f.getDefaultMessage()))
			.toList();
		ProblemDetail problem = ex.getBody();
		problem.setDetail("Requisição inválida.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/** Erros do próprio Spring MVC (rota inexistente, método errado, corpo ilegível...) também ganham o {@code code}. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> base = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (base == null || !(base.getBody() instanceof ProblemDetail problem)) {
			return base;
		}
		problem.setProperty("code", codeFor(statusCode.value()).name());
		return ResponseEntity.status(base.getStatusCode())
			.headers(base.getHeaders())
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	static ProblemDetail problem(ErrorCode code, String detail, String path) {
		HttpStatus known = HttpStatus.resolve(code.httpStatus());
		ProblemDetail problem = ProblemDetail.forStatus(code.httpStatus());
		problem.setTitle(known != null ? known.getReasonPhrase() : "Cancelled");
		problem.setDetail(detail);
		problem.setInstance(URI.create(path));
		problem.setProperty("code", code.name());
		return problem;
	}

	private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
		return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}

	private static ErrorCode codeFor(int status) {
		return switch (status) {
			case 404 -> ErrorCode.NOT_FOUND;
			case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> status >= 500 ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;
		};
	}
}
