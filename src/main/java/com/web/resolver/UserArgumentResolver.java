package com.web.resolver;

import com.web.annotation.SocialUser;
import com.web.domain.User;
import com.web.domain.enums.SocialType;
import com.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static com.web.domain.enums.SocialType.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(SocialUser.class) &&
                parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        HttpSession session =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getSession();

        User user = (User) session.getAttribute("user");
        return getUser(user, session);
    }

    private User getUser(User user, HttpSession session) {
        if(user == null) {
            try {
                // 1. SecurityContextHolder??? ????????? ????????? OAuth2AuthenticationToken ????????? ????????????.
                OAuth2AuthenticationToken authentication = (OAuth2AuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

                // 2. ????????? ???????????? getAttributes() ???????????? ????????? ????????? ??????????????? Map ???????????? ????????????. (???????????? ?????? ????????? ????????? ??????????????? ??????)
                Map<String, Object> map = authentication.getPrincipal().getAttributes();

                // getAuthorizedClientRegistrationId()??? ????????? ????????? ?????? ???????????? ?????? ??????
                User convertUser = convertUser(authentication.getAuthorizedClientRegistrationId(), map);


                //3. ???????????? ????????? ?????? ????????? ???????????????, User ????????? ????????????, ???????????? ?????? ??????????????? User ???????????? ????????????.
                user = userRepository.findByEmail(convertUser.getEmail());
                if (user == null) {
                    user = userRepository.save(convertUser);
                }

                setRoleIfNotSame(user, authentication, map);
                session.setAttribute("user", user);
            } catch(ClassCastException e) {
                return user;
            }
        }
        return user;
    }

    private void setRoleIfNotSame(User user, OAuth2AuthenticationToken authentication, Map<String, Object> map) {
        if(!authentication.getAuthorities().contains(new SimpleGrantedAuthority(user.getSocialType().getRoleType()))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(map, "N/A",
                    AuthorityUtils.createAuthorityList(user.getSocialType().getRoleType())));
        }
    }

    private User convertUser(String authority, Map<String, Object> map) {
        if(FACEBOOK.getValue().equals(authority)) return getModernUser(FACEBOOK, map);
        else if (GOOGLE.getValue().equals(authority)) return getModernUser(GOOGLE, map);
        else if (KAKAO.getValue().equals(authority)) return getKakaoUser(map);
        return null;
    }

    private User getKakaoUser(Map<String, Object> map) {
        HashMap<String, String> propertyMap = (HashMap<String, String>) map.get("properties");

        User user = User.builder()
                .name(propertyMap.get("profile_nickname"))
                .email(String.valueOf(map.get("account_email")))
                .principal(String.valueOf(map.get("id")))
                .socialType(KAKAO)
                .build();

        return user;

    }

    private User getModernUser(SocialType socialType, Map<String, Object> map) {
        return User.builder()
                .name(String.valueOf(map.get("name")))
                .email(String.valueOf(map.get("email")))
                .principal(String.valueOf(map.get("id")))
                .socialType(socialType)
                .build();
    }


}
